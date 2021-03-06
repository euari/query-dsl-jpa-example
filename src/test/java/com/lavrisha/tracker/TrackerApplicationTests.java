package com.lavrisha.tracker;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import javax.persistence.EntityManager;
import java.time.*;
import java.util.List;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = {AppConfig.class})
@DataJpaTest
@SpringBootTest(classes = TrackerApplication.class)
public class TrackerApplicationTests {

    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private StoryRepository storyRepository;
    @Autowired
    private EntityManager entityManager;

    @Test
    public void projectsCanHaveStories() {
        Project tractor = Project.builder().name("Tractor").build();
        Story story = Story.builder().title("Save me!").project(tractor).build();

        projectRepository.save(tractor);
        storyRepository.save(story);

        assertThat(storyRepository.findOne(story.getId()).getProject()).isEqualTo(tractor);
    }

    @Test
    public void searchStoriesByTitle() throws Exception {
        Project project = Project.builder().name("Tractor").build();
        Story johnDeere = Story.builder().title("Build John Deere").project(project).build();
        Story tykes = Story.builder().title("Build Tykes Tractor").project(project).build();

        Project interstellar = Project.builder().name("Interstellar Tractor").build();
        Story tractorBeam = Story.builder().title("Galactic Tractor Beam").project(interstellar).build();
        Story johnDeere1 = Story.builder().title("Build John Deere").project(interstellar).build();

        projectRepository.save(project);
        projectRepository.save(interstellar);
        storyRepository.save(asList(tykes, johnDeere, johnDeere1, tractorBeam));

        assertThat(storyRepository.search(
            project,
            SearchParams.builder().title("Build John Deere").build()
        )).containsOnly(johnDeere);
        assertThat(storyRepository.search(
            project,
            SearchParams.builder().title("Build John").build()
        )).containsOnly(johnDeere);
    }

    @Test
    public void searchesByRequester() throws Exception {
        Project project = Project.builder().name("Tractor").build();
        Story johnDeere = Story.builder().requester("Dear John").project(project).build();
        Story tykes = Story.builder().requester("Tykes Tractor").project(project).build();

        Project interstellar = Project.builder().name("Interstellar Tractor").build();
        Story tractorBeam = Story.builder().requester("Tractor Beam").project(interstellar).build();
        Story johnDeere1 = Story.builder().requester("Dear John").project(interstellar).build();

        projectRepository.save(project);
        projectRepository.save(interstellar);
        storyRepository.save(asList(tykes, johnDeere, johnDeere1, tractorBeam));

        assertThat(storyRepository.search(
            project,
            SearchParams.builder().requester("Dear John").build()
        )).containsOnly(johnDeere);
        assertThat(storyRepository.search(
            project,
            SearchParams.builder().requester("John").build()
        )).containsOnly(johnDeere);
    }

    @Test
    public void searchesByPoints() throws Exception {
        Project project = Project.builder().name("Tractor").build();
        Story threePointer = Story.builder().points(3).project(project).build();
        Story tykes = Story.builder().points(2).project(project).build();

        Project interstellar = Project.builder().name("Interstellar Tractor").build();
        Story tractorBeam = Story.builder().points(3).project(interstellar).build();
        Story johnDeere1 = Story.builder().points(2).project(interstellar).build();

        projectRepository.save(project);
        projectRepository.save(interstellar);
        storyRepository.save(asList(tykes, threePointer, johnDeere1, tractorBeam));

        assertThat(storyRepository.search(
            project,
            SearchParams.builder().points(3).build()
        )).containsOnly(threePointer);
    }

    @Test
    public void noSearchParamsNoResults() throws Exception {
        Project project = Project.builder().name("Tractor").build();
        Story nullTitle = Story.builder().title("null").project(project).build();

        projectRepository.save(project);
        storyRepository.save(asList(nullTitle, Story.builder().build()));

        assertThat(storyRepository.search(project, SearchParams.builder().build())).isEmpty();
    }

    @Test
    public void fetchProjectWithStoryDuringSearch() throws Exception {
        Project project = Project.builder().name("Tractor").build();
        Story johnDeere = Story.builder().title("Build John Deere").project(project).build();
        projectRepository.save(project);
        storyRepository.save(johnDeere);
        storyRepository.flush();
        projectRepository.flush();

        List<Story> stories = storyRepository.search(project, SearchParams.builder().title("John").build());
        Project project1 = stories.get(0).getProject();
        assertThat(project1).isEqualTo(project);
    }

    @Test
    public void updateStoryState() throws Exception {
        Project project = Project.builder().name("Tractor").build();
        Story johnDeere = Story.builder().title("Build John Deere").state("Started").project(project).build();

        projectRepository.save(project);
        storyRepository.save(johnDeere);

        storyRepository.updateState(johnDeere, "Finished");
        refreshHibernateCache(johnDeere);

        assertThat(storyRepository.findOne(johnDeere.getId()).getState()).isEqualTo("Finished");
    }

    @Test
    public void findTotalProjectPoints() throws Exception {
        Project project = Project.builder().name("Tractor").build();
        Story threePointer = Story.builder().title("Tre").points(3).project(project).build();
        Story tykes = Story.builder().title("Deuce").points(2).project(project).build();

        Project interstellar = Project.builder().name("Interstellar Tractor").build();
        Story tractorBeam = Story.builder().points(2).project(interstellar).build();
        Story johnDeere1 = Story.builder().points(1).project(interstellar).build();

        projectRepository.save(project);
        projectRepository.save(interstellar);
        storyRepository.save(asList(tykes, threePointer, johnDeere1, tractorBeam));

        ProjectPoints projectStories = storyRepository.findProjectStories(project);

        assertThat(projectStories).isEqualTo(new ProjectPoints("Tractor", 5));
    }

    @Test
    public void capturesRejection() throws Exception {
        Project project = Project.builder().name("Tractor").build();
        Story story = Story.builder().title("Tre").points(3).project(project).build();
        Clock clock = Clock.systemUTC();

        story.reject(clock);

        projectRepository.save(project);
        storyRepository.save(story);

        assertThat(story.getRejectedDate()).isEqualTo(LocalDate.now(clock));
        assertThat(story.getState()).isEqualTo("rejected");
    }

    @Test
    public void retrievesNumberOfRejectionsByDate() throws Exception {
        Project project = Project.builder().name("Tractor").build();
        projectRepository.save(project);

        Clock y2k = buildClock(2000, 1, 1);
        Story dateOverflow = Story.builder().project(project).build();
        dateOverflow.reject(y2k);

        Clock leap = buildClock(2004, 2, 29);
        Story leapStory = Story.builder().project(project).build();
        leapStory.reject(leap);

        storyRepository.save(asList(leapStory, dateOverflow));

        List<RejectionDate> rejectionDates = storyRepository.rejectionHistogram(project);

        assertThat(rejectionDates).contains(
            new RejectionDate(LocalDate.now(y2k), 1L),
            new RejectionDate(LocalDate.now(leap), 1L)
        );
    }

    private Clock buildClock(int year, int month, int dayOfMonth) {
        return Clock.fixed(LocalDate.of(year, month, dayOfMonth).atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    }

    private void refreshHibernateCache(Story story) {
        entityManager.refresh(story);
    }
}
